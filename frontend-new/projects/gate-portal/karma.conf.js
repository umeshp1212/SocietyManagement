// Karma configuration for the gate-portal (guard) app.
// Mirrors member-portal's config: headless Chrome/Edge for non-interactive runs.
module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma'),
    ],
    client: {
      jasmine: {},
      clearContext: false,
    },
    jasmineHtmlReporter: {
      suppressAll: true,
    },
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/gate-portal'),
      subdir: '.',
      reporters: [{ type: 'html' }, { type: 'text-summary' }],
    },
    reporters: ['progress', 'kjhtml'],
    hostname: '127.0.0.1',
    listenAddress: '127.0.0.1',
    browsers: ['ChromeHeadlessNoSandbox'],
    browserNoActivityTimeout: 120000,
    captureTimeout: 120000,
    browserDisconnectTimeout: 30000,
    browserDisconnectTolerance: 2,
    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        flags: [
          '--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage',
          '--disable-extensions', '--disable-background-networking',
          '--disable-sync', '--disable-default-apps', '--no-first-run',
          '--no-default-browser-check',
          '--user-data-dir=' + require('os').tmpdir() + '/karma-chrome-profile-gate',
          '--headless=new',
        ],
      },
      EdgeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        flags: [
          '--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage',
          '--disable-extensions', '--disable-background-networking',
          '--disable-sync', '--disable-default-apps', '--no-first-run',
          '--no-default-browser-check',
          '--user-data-dir=' + require('os').tmpdir() + '/karma-edge-profile-gate',
          '--headless=new',
        ],
      },
    },
    restartOnFileChange: true,
  });
};
