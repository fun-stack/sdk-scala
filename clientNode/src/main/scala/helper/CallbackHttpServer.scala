package funstack.client.node.helper

import cats.effect.{IO, Resource}
import funstack.client.node.helper.facades.{Http, HttpRequest, HttpResponse, Url}

import scala.scalajs.js

object CallbackHttpServer {
  private val serverResource =
    Resource.make(IO(Http.createServer()))(server => IO(server.close()))

  def authCode(port: Int): IO[String] = serverResource.use { server =>
    IO.async_ { cb =>
      val requestListener: js.Function2[HttpRequest, HttpResponse, Unit] = { (request, response) =>
        val url           = Url.parse(request.url, parseQueryString = true)
        val codeParameter = url.query.get("code")

        codeParameter match {
          case Some(code) =>
            response.writeHead(200)
            response.end(statusHtml("You were successfully authorized!", success = true))
            cb(Right(code))
          case None       =>
            response.writeHead(400)
            response.end(statusHtml("Not authorized!", success = false))
            cb(Left(new Exception("No authorization code")))
        }
      }

      server.on("request", requestListener)

      server.listen(port)
    }
  }

  private def statusHtml(message: String, success: Boolean): String = {
    val (statusColor, statusText) = success match {
      case true  => ("#88B04B", "Success")
      case false => ("#B33A3A", "Failure")
    }

    s"""
       |<html>
       |  <head>
       |    <style>
       |      body {
       |        text-align: center;
       |        padding: 40px 0;
       |        background: #EBF0F5;
       |      }
       |      h1 {
       |        color: ${statusColor};
       |        font-weight: 900;
       |        font-size: 40px;
       |        margin-bottom: 10px;
       |      }
       |      p {
       |        color: #404F5E;
       |        font-size:20px;
       |        margin: 0;
       |      }
       |      .banner {
       |         background: ${statusColor};
       |         height: 20px;
       |         width: 100%;
       |         margin: 0;
       |       }
       |      .content {
       |        padding: 60px;
       |      }
       |      .card {
       |        background: white;
       |        border-radius: 4px;
       |        box-shadow: 0 2px 3px #C8D0D8;
       |        display: inline-block;
       |        margin: 0 auto;
       |      }
       |    </style>
       |  </head>
       |  <body>
       |    <div class="card">
       |      <div class="banner"></div>
       |      <div class="content">
       |        <h1>${statusText}</h1>
       |        <p>${message}</p>
       |      <div>
       |    </div>
       |  </body>
       |</html>
       |""".stripMargin
  }
}
