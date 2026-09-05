package com.example.printshop.service;

import com.example.printshop.entity.FileInfo;
import java.util.List;

public interface FileInfoService {
    FileInfo getById(Integer id);
    List<FileInfo> getByOrderId(Integer orderId);
    int add(FileInfo file);
    int delete(Integer id);
    List<FileInfo> getAll();
}