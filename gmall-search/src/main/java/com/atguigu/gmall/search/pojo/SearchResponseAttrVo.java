package com.atguigu.gmall.search.pojo;

import lombok.Data;

import java.util.List;

@Data
public class SearchResponseAttrVo {
    //AttrID
    private Long attrId;
    //AttrName
    private String attrName;

    //AttrValues
    private List<String> attrValues;
}
