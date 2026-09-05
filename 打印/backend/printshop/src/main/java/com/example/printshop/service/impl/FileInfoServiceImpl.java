package com.example.printshop.service.impl;

import com.example.printshop.entity.FileInfo;
import com.example.printshop.mapper.FileInfoMapper;
import com.example.printshop.service.FileInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class FileInfoServiceImpl implements FileInfoService {
    @Autowired
    private FileInfoMapper mapper;

    @Override
    public FileInfo getById(Integer id) {
        return mapper.selectById(id);
    }

    @Override
    public List<FileInfo> getByOrderId(Integer orderId) {
        return mapper.selectByOrderId(orderId);
    }

    @Override
    public int add(FileInfo file) {
        return mapper.insert(file);
    }

    @Override
    public int delete(Integer id) {
        return mapper.delete(id);
    }

    @Override
    public List<FileInfo> getAll() {
        return mapper.selectAll();
    }
}