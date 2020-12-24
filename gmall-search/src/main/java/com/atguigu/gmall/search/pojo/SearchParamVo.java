package com.atguigu.gmall.search.pojo;

import lombok.Data;

import java.util.List;

@Data
public class SearchParamVo {

    //检索关键字
    private String keyword;

    //品牌的Id
    private List<Long> brandId;

    //分类的id
    private List<Long> categoryId;

    //规格参数 props=4：8G-12G & props=5：128G-256G &priceFrom = 1000 &priceTo=3000 &store = true
    private List<String> props;

    //价格区间
    private  Double priceFrom;
    private  Double priceTo;

    //是否有货
    private Boolean store;

    //排序  0是总和排序  1是价格降序   2是价格的升序   3是销量的降序   4是新品的降序
    private Integer sort;

    //分页
    private Integer pageNum = 1;
    private final Integer pageSize = 20;

}
