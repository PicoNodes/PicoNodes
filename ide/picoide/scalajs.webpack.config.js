var path = require('path');

module.exports = {
    "entry": {
        "picoide-fastopt": [
            path.resolve(__dirname, "picoide-fastopt.js")
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
