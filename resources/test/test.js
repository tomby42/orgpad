var page = require('webpage').create();
var url = phantom.args[0];

console.log('Phantom js started');

page.onConsoleMessage = function (message) {
  console.log(message);
};

page.open(url, function (status) {
  page.evaluate(function(){
    orgpad.test.run();
  });
  phantom.exit(0);
});
