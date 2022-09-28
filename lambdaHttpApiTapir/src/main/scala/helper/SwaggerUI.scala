package funstack.lambda.http.api.tapir.helper

import scala.scalajs.js

object SwaggerUI {
  private val version = "4"

  def html(title: String, openApiSpecPath: String) = s"""
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <meta
    name="description"
    content="SwaggerUI"
  />
  <title>${title}</title>
  <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@${version}/swagger-ui.css" />
</head>
<body>
<div id="swagger-ui"></div>
<script src="https://unpkg.com/swagger-ui-dist@${version}/swagger-ui-bundle.js" crossorigin></script>
<script>
  window.onload = () => {
    window.ui = SwaggerUIBundle({
      url: '${openApiSpecPath}',
      dom_id: '#swagger-ui',
    });
  };
</script>
</body>
</html>"""

}
