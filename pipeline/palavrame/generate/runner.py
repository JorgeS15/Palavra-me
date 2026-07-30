"""Execução do AMALIA em batch (plano 5.3).

A decisão que torna isto viável: **não se serve nada**. Gera-se um dataset,
uma vez, sem pressa. A 2 tokens/segundo, um lema demora segundos e o batch
inteiro corre durante dias em background — a latência é irrelevante quando não
há ninguém à espera. É por isso que um GGUF quantizado num portátil substitui
a A100 que a documentação do modelo pede.

Tudo o que sai daqui é marcado `generated=True`, e vai a revisão humana antes
de entrar na DB (plano 10.8: sem exceções).
"""

from __future__ import annotations

import json
import urllib.request
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Callable, Iterable, Protocol, Sequence

from ..schema import Example, MergedEntry
from .prompts import build_example_prompt
from .validators import PosChecker, validate_example

# Modelo recomendado pelo plano. Quantizado, cabe em memória de portátil.
DEFAULT_MODEL = "amalia-llm/AMALIA-9B-0626-DPO"
DEFAULT_OLLAMA_URL = "http://localhost:11434/api/generate"

# Quantas vezes se volta a pedir quando a validação rejeita. Passado isto, a
# aceção fica sem exemplo — o que é melhor do que um exemplo mau.
MAX_ATTEMPTS = 3


class Backend(Protocol):
    """Qualquer coisa que transforme um prompt numa frase."""

    name: str

    def complete(self, prompt: str) -> str: ...


class EchoBackend:
    """Backend de teste: devolve o que lhe mandarem.

    Existe para que o pipeline de geração — prompts, validação, retentativas,
    revisão — seja testável sem modelo nenhum instalado.
    """

    name = "echo"

    def __init__(self, responses: Callable[[str], str] | Sequence[str]):
        self._responses = responses
        self._i = 0

    def complete(self, prompt: str) -> str:
        if callable(self._responses):
            return self._responses(prompt)
        if self._i < len(self._responses):
            out = self._responses[self._i]
            self._i += 1
            return out
        return ""


class OllamaBackend:
    """AMALIA via Ollama local.

    Nota: isto abre um socket, mas para `localhost`. Não é uma fonte de dados
    e não viola a regra de a rede viver só em `sources/` — o modelo corre na
    máquina de quem faz a build.
    """

    name = "amalia"

    def __init__(
        self,
        model: str = DEFAULT_MODEL,
        url: str = DEFAULT_OLLAMA_URL,
        temperature: float = 0.7,
        timeout: int = 300,
    ):
        self.model = model
        self.url = url
        self.temperature = temperature
        self.timeout = timeout

    def complete(self, prompt: str) -> str:
        payload = json.dumps(
            {
                "model": self.model,
                "prompt": prompt,
                "stream": False,
                "options": {
                    "temperature": self.temperature,
                    # Uma frase. Mais do que isto é o modelo a divagar.
                    "num_predict": 120,
                    "stop": ["\n\n", "Frase:", "Palavra:"],
                },
            }
        ).encode("utf-8")
        request = urllib.request.Request(
            self.url, data=payload, headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(request, timeout=self.timeout) as response:
            data = json.loads(response.read().decode("utf-8"))
        return (data.get("response") or "").strip()


@dataclass
class Candidate:
    """Um exemplo gerado à espera de revisão."""

    lemma: str
    pos: str
    sense_ord: int
    definition: str
    sentence: str
    model: str
    attempts: int
    status: str = "pendente"          # 'pendente'|'aprovado'|'rejeitado'
    rejection_reasons: list[str] = field(default_factory=list)

    def to_example(self) -> Example:
        return Example(
            sentence=self.sentence,
            source="amalia",
            source_ref=self.model,
            variant="pt-PT",
            generated=True,
            sense_ord=self.sense_ord,
        )


def generate_examples(
    entries: Iterable[MergedEntry],
    backend: Backend,
    *,
    only_missing: bool = True,
    pos_checker: PosChecker | None = None,
    max_attempts: int = MAX_ATTEMPTS,
    progress: Callable[[str], None] | None = None,
) -> list[Candidate]:
    """Gera candidatos para as aceções sem exemplo.

    `only_missing=True` é o modo normal: o AMALIA é o último degrau da
    cascata, só entra onde o Tatoeba e o Leipzig não chegaram (plano 4.3).
    """
    candidates: list[Candidate] = []

    for entry in entries:
        covered = {e.sense_ord for e in entry.examples if not e.generated}
        known_forms = [f.form for f in entry.forms] + [entry.lemma]

        for sense in entry.senses:
            if only_missing and (sense.ord in covered or None in covered):
                continue
            prompt = build_example_prompt(entry.lemma, entry.pos, sense.definition)

            accepted: Candidate | None = None
            last_reasons: list[str] = []
            for attempt in range(1, max_attempts + 1):
                raw = backend.complete(prompt)
                sentence = _clean(raw)
                if not sentence:
                    last_reasons = ["resposta vazia do modelo"]
                    continue
                result = validate_example(
                    sentence, entry.lemma, entry.pos, sense.definition,
                    known_forms=known_forms, pos_checker=pos_checker,
                )
                if result.ok:
                    accepted = Candidate(
                        lemma=entry.lemma, pos=entry.pos, sense_ord=sense.ord,
                        definition=sense.definition, sentence=sentence,
                        model=getattr(backend, "name", "desconhecido"),
                        attempts=attempt,
                    )
                    break
                last_reasons = result.reasons

            if accepted:
                candidates.append(accepted)
                if progress:
                    progress(f"[ok]  {entry.lemma} ({sense.ord})")
            else:
                candidates.append(
                    Candidate(
                        lemma=entry.lemma, pos=entry.pos, sense_ord=sense.ord,
                        definition=sense.definition, sentence="",
                        model=getattr(backend, "name", "desconhecido"),
                        attempts=max_attempts, status="rejeitado",
                        rejection_reasons=last_reasons,
                    )
                )
                if progress:
                    progress(f"[falha] {entry.lemma} ({sense.ord}): "
                             f"{'; '.join(last_reasons)}")

    return candidates


def _clean(raw: str) -> str:
    """Tira o que os modelos gostam de acrescentar apesar de lhes pedirem."""
    text = raw.strip()
    for prefix in ("Frase:", "Exemplo:", "Resposta:", "-", "*", "•"):
        if text.startswith(prefix):
            text = text[len(prefix):].strip()
    if len(text) > 1 and text[0] in "«\"'“" and text[-1] in "»\"'”":
        text = text[1:-1].strip()
    # Só a primeira frase: o modelo às vezes dá duas.
    for end in (". ", "! ", "? "):
        idx = text.find(end)
        if idx > 0:
            text = text[: idx + 1]
            break
    return text.strip()


def save_candidates(candidates: Sequence[Candidate], path: Path) -> int:
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        for candidate in candidates:
            fh.write(json.dumps(asdict(candidate), ensure_ascii=False) + "\n")
    return len(candidates)


def load_candidates(path: Path) -> list[Candidate]:
    out = []
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                out.append(Candidate(**json.loads(line)))
    return out
