package com.example.printshop.mapper;

import com.example.printshop.entity.FileInfo;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface FileInfoMapper {
    FileInfo selectById(Integer id);
    List<FileInfo> selectByOrderId(Integer orderId);
    int insert(FileInfo fileInfo);
    int delete(Integer id);
    List<FileInfo> selectAll();
}