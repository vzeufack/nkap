package com.kmercoders.nkap.bulkupload;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.kmercoders.nkap.bulkupload.csv.CsvValidationException;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/bulk-upload")
public class BulkUploadController {

    private final BulkUploadService bulkUploadService;

    public BulkUploadController(BulkUploadService bulkUploadService) {
        this.bulkUploadService = bulkUploadService;
    }

    @PostMapping("/preview")
    public ResponseEntity<BulkUploadPreviewResponse> preview(@RequestParam("accountId") Long accountId,
                                                              @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(bulkUploadService.previewUpload(accountId, file));
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@Valid @RequestBody BulkUploadConfirmRequest request,
                                     BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            Map<String, String> errors = bindingResult.getFieldErrors().stream()
                .collect(Collectors.toMap(
                    fe -> fe.getField(),
                    fe -> fe.getDefaultMessage(),
                    (first, second) -> first
                ));
            return ResponseEntity.badRequest().body(errors);
        }

        return ResponseEntity.ok(bulkUploadService.confirmUpload(request));
    }

    @ExceptionHandler(CsvValidationException.class)
    public ResponseEntity<Map<String, Object>> handleCsvValidationException(CsvValidationException e) {
        return ResponseEntity.badRequest().body(Map.of("errors", e.getErrors()));
    }
}
