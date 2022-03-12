# fun-stack

Client library in scala for working with fun-stack infrastructure.

See Example for how to use it:
- Scala Project Template: [fun-stack-example](https://github.com/fun-stack/fun-stack-example)

See terraform module for the corresponding AWS infrastructure:
- Terraform Module: [terraform-aws-fun](https://github.com/fun-stack/terraform-aws-fun) (version `>= 0.5.0`)

See local development module for mocking the AWS infrastructure locally:
- Local Module: [fun-stack-local](https://github.com/fun-stack/fun-stack-local) (version `>= 0.3.0`)

## Get started

Get latest release:
```scala
libraryDependencies += "com.github.fun-stack" %%% "fun-stack-web" % "0.4.0"
libraryDependencies += "com.github.fun-stack" %%% "fun-stack-lambda-http" % "0.4.0"
libraryDependencies += "com.github.fun-stack" %%% "fun-stack-lambda-ws" % "0.4.0"
libraryDependencies += "com.github.fun-stack" %%% "fun-stack-lambda-eventauthorizer" % "0.4.0"
libraryDependencies += "com.github.fun-stack" %%% "fun-stack-backend" % "0.4.0"
```
