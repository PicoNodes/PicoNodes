var path = require('path');
var HardSourceWebpackPlugin = require('hard-source-webpack-plugin');

module.exports = {
    "entry": {
        "picoide-fastopt": [
            path.resolve(__dirname, "picoide-fastopt-entrypoint.js")
        ],
        "picoide-opt": [
            path.resolve(__dirname, "picoide-opt-entrypoint.js")
        ]
    },
    "resolve": {
        "modules": [
            path.resolve(__dirname, "node_modules")
        ]
    },
    "module": {
        rules: [{
            test: /\.scss$/,
            use: ["style-loader", "css-loader", "sass-loader"]
        }]
    },
    "output": {
        "path": __dirname,
        "filename": "[name]-library.js",
        "library": "ScalaJSBundlerLibrary",
        "libraryTarget": "var"
    },
    "plugins": [
        new HardSourceWebpackPlugin()
    ]
};
