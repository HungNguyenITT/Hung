package API.ISO8583.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import API.ISO8583.dto.ISO8583RequestDTO;
import API.ISO8583.service.ISO8583Service;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/iso8583/process")
public class ISO8583Controller {

    @Autowired
    private ISO8583Service iso8583Service;
    @PostMapping("/inquiry")
    public CompletableFuture<ResponseEntity<String>> inquiry(@RequestBody ISO8583RequestDTO dto) {
        return iso8583Service.processISO8583RequestAsync(dto)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex ->
                    ResponseEntity.status(500)
                        .body("{\"error\":\"Error processing inquiry: " + ex.getMessage() + "\"}")
                );
    }
    
    @PostMapping("/payment")
    public CompletableFuture<ResponseEntity<String>> payment(@RequestBody ISO8583RequestDTO dto) {
        return iso8583Service.processISO8583RequestAsync(dto)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex ->
                    ResponseEntity.status(500)
                        .body("{\"error\":\"Error processing payment: " + ex.getMessage() + "\"}")
                );
    }
}

