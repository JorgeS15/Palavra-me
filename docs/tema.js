// Tema do site: segue o sistema por omissão; o utilizador pode fixar Claro ou Escuro.
(function () {
  var CHAVE = 'palavrame-tema';
  var raiz = document.documentElement;
  var escuroSistema = window.matchMedia('(prefers-color-scheme: dark)');

  function lido() {
    try { return localStorage.getItem(CHAVE) || 'sistema'; } catch (e) { return 'sistema'; }
  }
  function escuroAtivo(t) { return t === 'escuro' || (t === 'sistema' && escuroSistema.matches); }

  function aplicar(t) {
    if (t === 'sistema') raiz.removeAttribute('data-tema'); else raiz.setAttribute('data-tema', t);
    var cor = escuroAtivo(t) ? '#14110D' : '#F4EEE3';
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', cor);
    var botoes = document.querySelectorAll('[data-tema-opcao]');
    for (var i = 0; i < botoes.length; i++) {
      botoes[i].setAttribute('aria-pressed', botoes[i].getAttribute('data-tema-opcao') === t ? 'true' : 'false');
    }
  }

  aplicar(lido());

  document.addEventListener('DOMContentLoaded', function () {
    aplicar(lido());
    var botoes = document.querySelectorAll('[data-tema-opcao]');
    for (var i = 0; i < botoes.length; i++) {
      botoes[i].addEventListener('click', function () {
        var t = this.getAttribute('data-tema-opcao');
        try { if (t === 'sistema') localStorage.removeItem(CHAVE); else localStorage.setItem(CHAVE, t); } catch (e) {}
        aplicar(t);
      });
    }
  });
  escuroSistema.addEventListener && escuroSistema.addEventListener('change', function () { aplicar(lido()); });
})();
