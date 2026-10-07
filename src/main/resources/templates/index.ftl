<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Knote</title>
    <link rel="stylesheet" href="/main.css">
</head>
<body>
<main class="container">
    <h1>Knote</h1>
    <p class="subtitle">Escribe notas en Markdown y adjunta imágenes.</p>

    <form action="/note" method="post" enctype="multipart/form-data" class="card">
        <textarea name="description" rows="5" placeholder="Escribe tu nota... (Markdown)">${(description!"")?html}</textarea>
        <div class="row">
            <input type="file" name="image" accept="image/*">
            <input type="submit" name="upload" value="Upload" class="btn secondary">
            <input type="submit" name="publish" value="Publish" class="btn primary">
        </div>
    </form>

    <section>
        <#list notes as note>
            <article class="card note">${note}</article>
        </#list>
    </section>
</main>
</body>
</html>

