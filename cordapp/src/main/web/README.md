# Web packages

## How to build

`./build.sh`

This will place the minimised distribution of each website in the respective 
`src/main/resources/web/<site>` direcotry.

## How to live code a website

In the IntelliJ runner, setup the `Working Directory` to `<project-root>/cordapp/src/main/resources`.
In a shell, `cd web/<site>` and run `npm run watch`. This will setup an automatic watch on the site's files and will rebuild on change.
Make sure you keep an eye on its output .. it's running ESLint!


