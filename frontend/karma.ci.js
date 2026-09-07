// Temporary CI karma config used to run the transaction component specs
// non-interactively. It reuses the base config but overrides the headless
// Chrome launcher so Karma controls the remote-debugging port itself
// (the base config pins --remote-debugging-port, which prevents capture in
// this environment).
const base = require('./karma.conf.js');

module.exports = function (config) {
  // Apply the base configuration first.
  base(config);

  // Override the custom launcher to drop the fixed remote-debugging-port so
  // Karma/Chrome can negotiate the debugging connection automatically.
  config.set({
    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        flags: [
          '--no-sandbox',
          '--disable-gpu',
          '--disable-dev-shm-usage',
          '--disable-extensions',
          '--disable-background-networking',
          '--disable-sync',
          '--disable-default-apps',
          '--no-first-run',
          '--no-default-browser-check',
          '--headless=new',
        ],
      },
    },
  });
};
