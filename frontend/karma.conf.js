// Karma configuration for the Society Management frontend.
// Runs Angular component/unit specs under headless Chrome so `ng test`
// works non-interactively (CI-style) without a visible browser.
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
      dir: require('path').join(__dirname, './coverage/society-management'),
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
          '--remote-debugging-port=9333',
        ],
      },
    },
    restartOnFileChange: true,
  });
};
