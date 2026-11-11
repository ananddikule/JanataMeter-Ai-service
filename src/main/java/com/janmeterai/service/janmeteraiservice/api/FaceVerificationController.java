package com.janmeterai.service.janmeteraiservice.api;

import com.janmeterai.service.janmeteraiservice.model.FaceMatchResponse;
import com.janmeterai.service.janmeteraiservice.service.FaceVerificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/face-verification")
public class FaceVerificationController {
    private final FaceVerificationService service;

    public FaceVerificationController(FaceVerificationService service) { this.service = service; }

    @PostMapping(path = "/match", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FaceMatchResponse match(@RequestPart("referenceImage") MultipartFile reference,
                                   @RequestPart("liveImage") MultipartFile live) {
        return service.match(reference, live);
    }

    @PostMapping(path = "/extract-passport-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> extractPassportPhoto(@RequestPart("documentImage") MultipartFile documentImage) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .body(service.extractPassportPhoto(documentImage));
    }
}
