package com.cug.geotext.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;

public record PageResult<T>(List<T> records, long total, long page, long size, long pages) {
    public static <T> PageResult<T> from(IPage<T> source) {
        return new PageResult<>(source.getRecords(), source.getTotal(), source.getCurrent(), source.getSize(), source.getPages());
    }
}

