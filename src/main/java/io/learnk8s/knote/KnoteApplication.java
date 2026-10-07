package io.learnk8s.knote;

import io.minio.MinioClient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@SpringBootApplication
public class KnoteApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnoteApplication.class, args);
    }
}

/* ---------- Modelo ---------- */

@Document(collection = "notes")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
class Note {
    @Id
    private String id;
    private String description;

    @Override
    public String toString() {
        return description;
    }
}

/* ---------- Repositorio (Spring Data genera la implementación) ---------- */

interface NotesRepository extends MongoRepository<Note, String> {
}

/* ---------- Configuración (MinIO) ---------- */

@ConfigurationProperties(prefix = "knote")
class KnoteProperties {

    @Value("${minio.host:localhost}")
    private String minioHost;

    @Value("${minio.bucket:image-storage}")
    private String minioBucket;

    @Value("${minio.access.key:}")
    private String minioAccessKey;

    @Value("${minio.secret.key:}")
    private String minioSecretKey;

    @Value("${minio.useSSL:false}")
    private boolean minioUseSSL;

    @Value("${minio.reconnect.enabled:true}")
    private boolean minioReconnectEnabled;

    public String getMinioHost() {
        return minioHost;
    }

    public String getMinioBucket() {
        return minioBucket;
    }

    public String getMinioAccessKey() {
        return minioAccessKey;
    }

    public String getMinioSecretKey() {
        return minioSecretKey;
    }

    public boolean isMinioUseSSL() {
        return minioUseSSL;
    }

    public boolean isMinioReconnectEnabled() {
        return minioReconnectEnabled;
    }
}

/* ---------- Controlador ---------- */

@Controller
@EnableConfigurationProperties(KnoteProperties.class)
class KNoteController {

    @Autowired
    private NotesRepository notesRepository;

    @Autowired
    private KnoteProperties properties;

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    private MinioClient minioClient;

    @PostConstruct
    public void init() throws InterruptedException {
        initMinio();
    }

    /**
     * Se conecta a MinIO y crea el bucket. Si MinIO todavía no está listo
     * (por ejemplo, su Pod arranca con retraso) reintenta cada 5 segundos,
     * porque Kubernetes puede iniciar los componentes en cualquier orden.
     */
    private void initMinio() throws InterruptedException {
        boolean success = false;
        while (!success) {
            try {
                minioClient = new MinioClient("http://" + properties.getMinioHost() + ":9000",
                        properties.getMinioAccessKey(),
                        properties.getMinioSecretKey(),
                        false);
                if (minioClient.bucketExists(properties.getMinioBucket())) {
                    System.out.println("> Bucket already exists.");
                } else {
                    minioClient.makeBucket(properties.getMinioBucket());
                }
                success = true;
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("> Minio Reconnect: " + properties.isMinioReconnectEnabled());
                if (properties.isMinioReconnectEnabled()) {
                    Thread.sleep(5000);
                } else {
                    success = true;
                }
            }
        }
        System.out.println("> Minio initialized!");
    }

    @GetMapping("/")
    public String index(Model model) {
        getAllNotes(model);
        return "index";
    }

    @PostMapping("/note")
    public String saveNotes(@RequestParam(value = "image", required = false) MultipartFile file,
                            @RequestParam String description,
                            @RequestParam(required = false) String publish,
                            @RequestParam(required = false) String upload,
                            Model model) throws Exception {

        if (publish != null && publish.equals("Publish")) {
            saveNote(description, model);
            return "redirect:/";
        }
        if (upload != null && upload.equals("Upload")) {
            if (file != null && file.getOriginalFilename() != null
                    && !file.getOriginalFilename().isEmpty()) {
                uploadImage(file, description, model);
            }
            getAllNotes(model);
            return "index";
        }
        getAllNotes(model);
        return "index";
    }

    /** Sirve las imágenes leyéndolas desde MinIO (ya no desde el disco del Pod). */
    @GetMapping("/img/{name}")
    @ResponseBody
    public ResponseEntity<byte[]> getImageByName(@PathVariable String name) {
        try (InputStream imageStream = minioClient.getObject(properties.getMinioBucket(), name)) {
            byte[] bytes = IOUtils.toByteArray(imageStream);
            String contentType = URLConnection.guessContentTypeFromName(name);
            MediaType mediaType = MediaType.parseMediaType(contentType != null ? contentType : MediaType.IMAGE_PNG_VALUE);
            return ResponseEntity.ok().contentType(mediaType).body(bytes);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private void getAllNotes(Model model) {
        List<Note> notes = notesRepository.findAll();
        Collections.reverse(notes);
        model.addAttribute("notes", notes);
    }

    private void saveNote(String description, Model model) {
        if (description != null && !description.trim().isEmpty()) {
            // Se traduce Markdown a HTML antes de guardar
            Node document = parser.parse(description.trim());
            String html = renderer.render(document);
            notesRepository.save(new Note(null, html));
            // Limpia el textarea después de publicar
            model.addAttribute("description", "");
        }
    }

    private void uploadImage(MultipartFile file, String description, Model model) throws Exception {
        String extension = FilenameUtils.getExtension(file.getOriginalFilename());
        String fileId = UUID.randomUUID().toString() + (extension.isEmpty() ? "" : "." + extension.toLowerCase());
        minioClient.putObject(properties.getMinioBucket(), fileId, file.getInputStream(),
                file.getSize(), null, null, file.getContentType());
        model.addAttribute("description", description + " ![](/img/" + fileId + ")");
    }
}
