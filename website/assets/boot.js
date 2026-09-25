/* Runs before first paint: turns on the JS-only styles, and makes sure the wordmark
   shows even if the display font never loads. */
document.documentElement.classList.add('js');
setTimeout(function () {
  var w = document.getElementById('wordmark');
  if (w) w.classList.add('go');
}, 2500);
