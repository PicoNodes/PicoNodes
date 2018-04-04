const path = require('path');
const fs = require('fs');
const HardSourceWebpackPlugin = require('hard-source-webpack-plugin');

let entryPoints = {};
for (const entryPoint of ["picoide-fastopt", "picoide-opt"]) {
    const entryPointPath = path.resolve(__dirname, `${entryPoint}-entrypoint.js`);
    if (fs.existsSync(entryPointPath)) {
        entryPoints[entryPoint] = entryPointPath;
    }
}

module.exports = {
    "entry": entryPoints,
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
