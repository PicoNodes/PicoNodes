var path = require('path');

module.exports = {
    "entry": {
        "picoide-fastopt": [
            path.resolve(__dirname, "picoide-fastopt.js")
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
        "filename": "[name]-bundle.js"
    }
}
