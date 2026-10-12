// Runs before the app loads so the page never flashes the wrong theme.
(function () {
  try {
    var saved = localStorage.getItem("florin-theme");
    if (saved === "light" || saved === "dark") document.documentElement.dataset.theme = saved;
  } catch (e) { /* storage blocked: follow the system theme */ }
})();
