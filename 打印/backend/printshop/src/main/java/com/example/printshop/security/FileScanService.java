package com.example.printshop.security;

import com.example.printshop.common.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

@Service
public class FileScanService {
    private static final int CHUNK_SIZE = 8192;

    private final boolean enabled;
    private final String host;
    private final int port;
    private final int timeoutMs;
    private final boolean failClosed;

    @Autowired
    public FileScanService(@Value("${printshop.security.virus-scan.enabled:false}") boolean enabled,
                           @Value("${printshop.security.virus-scan.host:127.0.0.1}") String host,
                           @Value("${printshop.security.virus-scan.port:3310}") int port,
                           @Value("${printshop.security.virus-scan.timeout-ms:5000}") int timeoutMs,
                           @Value("${printshop.security.virus-scan.fail-closed:true}") boolean failClosed) {
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
        this.failClosed = failClosed;
    }

    public void scan(MultipartFile file) {
        if (!enabled || file == null || file.isEmpty()) {
            return;
        }
        try {
            String response = scanWithClamAv(file);
            if (response == null || response.isBlank()) {
                handleScannerFailure("empty scan response");
                return;
            }
            if (response.contains("FOUND")) {
                throw ApiException.badRequest("文件安全扫描未通过");
            }
            if (!response.contains("OK")) {
                handleScannerFailure(response);
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            handleScannerFailure(ex.getMessage());
        }
    }

    private String scanWithClamAv(MultipartFile file) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            try (DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                 InputStream input = file.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
                byte[] buffer = new byte[CHUNK_SIZE];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.writeInt(read);
                    output.write(buffer, 0, read);
                }
                output.writeInt(0);
                output.flush();
                return reader.readLine();
            }
        }
    }

    private void handleScannerFailure(String detail) {
        if (failClosed) {
            throw ApiException.serviceUnavailable("文件安全扫描服务不可用");
        }
    }
}
