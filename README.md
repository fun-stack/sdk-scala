# sdk-scala

SDK in scala for working with the fun-stack infrastructure.

## Links

Example on how to use it:
- Fun Scala Template: [example](https://github.com/fun-stack/example)

Terraform module for the corresponding AWS infrastructure:
- Fun Terraform Module: [terraform-aws-fun](https://github.com/fun-stack/terraform-aws-fun) (version `>= 0.5.0`)

See local development module for mocking the AWS infrastructure locally:
- Fun Local Environment: [local-env](https://github.com/fun-stack/local-env) (version `>= 0.3.0`)

## Get started

Get latest release:
```scala
libraryDependencies += "com.github.fun-stack" %%% "fun-stack-web" % "0.4.0"
libraryDependencies += "com.github.fun-stack" %%% "fun-stack-lambda-http" % "0.4.0"
libraryDependencies += "com.github.fun-stack" %%% "fun-stack-lambda-ws" % "0.4.0"
libraryDependencies += "com.github.fun-stack" %%% "fun-stack-lambda-event-authorizer" % "0.4.0"
libraryDependencies += "com.github.fun-stack" %%% "fun-stack-backend" % "0.4.0"
```
