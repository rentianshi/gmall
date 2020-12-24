package com.atguigu.gmall.search.pojo;

import com.atguigu.gmall.pms.entity.AttrEntity;
import com.atguigu.gmall.pms.entity.BrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import lombok.Data;

import java.util.List;

@Data
public class SearchResponseVo {

    //过滤字段
    //品牌Id
    private List<BrandEntity> brands;
    //分类
    private List<CategoryEntity> categories;
    //attr属性值[{},{}] 一个花括号代表一类参数
    private List<SearchResponseAttrVo> filters;


    //排序、
    //价格区间、
    //是否有货、
    //分页
    private Integer pageNum;
    private Integer pageSize;
    private Long total;


    //goods集合
    private List<Goods> goodsList;
}
