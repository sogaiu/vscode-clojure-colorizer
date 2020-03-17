# vscode-clojure-colorizer

## What

A rainbow parens implementation for Clojure code...with a twist.

## How

To install from a .vsix:

```
# clone repository
cd ~/src
git clone https://github.com/sogaiu/vscode-clojure-colorizer
cd vscode-clojure-colorizer

# command line install of extension
visual-studio-code --install-extension clojure-colorizer-*.vsix
```

Alternatively, via the VSCode GUI:

* `File > Preferences > Extensions`
* Click on the `...` at the top right of the EXTENSIONS area
* Choose `Install from VSIX...` and find the .vsix file

To run the extension from source:

```
# clone repository
cd ~/src
git clone https://github.com/sogaiu/vscode-clojure-colorizer
cd vscode-clojure-colorizer

# create `node_modules` and populate with dependencies
npm install

# open the root folder in VSCode
visual-studio-code . # or whatever vscode is called on your system
```

Now run the extension in an Extension Development Host by pressing `F5`, or choosing `Debug` > `Start`

View some Clojure code and enjoy the nice colors.

## Acknowledgments

See [tree-sitter-clojure](https://github.com/sogaiu/tree-sitter-clojure)'s [section of the same name](https://github.com/sogaiu/tree-sitter-clojure#acknowledgments).
