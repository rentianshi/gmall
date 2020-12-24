package com.atguigu.gmall.search.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.pms.entity.BrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchParamVo;
import com.atguigu.gmall.search.pojo.SearchResponseAttrVo;
import com.atguigu.gmall.search.pojo.SearchResponseVo;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SearchService {
    @Autowired
    private RestHighLevelClient restHighLevelClient;

    public SearchResponseVo search(SearchParamVo paramVo) {

        try {
            SearchRequest searchRequest = new SearchRequest(new String[] {"goods"},buildDsl(paramVo));
            SearchResponse response = this.restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            //解析结果集
            SearchResponseVo responseVo =  this.parseResult(response);
            //从查询条件中获取分页的内容
            responseVo.setPageNum(paramVo.getPageNum());
            responseVo.setPageSize(paramVo.getPageSize());
            return responseVo;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private SearchResponseVo parseResult(SearchResponse response) {
        SearchResponseVo responseVo = new SearchResponseVo();
        //解析hits
        SearchHits hits = response.getHits();
        //总命中数
        responseVo.setTotal(hits.totalHits);
        //内层中的hitsHits
        SearchHit[] hitsHits = hits.getHits();
        List<Goods> goodsList = Stream.of(hitsHits).map(hitsHit -> {
            String json = hitsHit.getSourceAsString();
            //将_source反序列化为goods对象
            Goods goods = JSON.parseObject(json, Goods.class);
            //获取高亮结果集，替换掉普通的title
            Map<String, HighlightField> highlightFields = hitsHit.getHighlightFields();
            HighlightField highlightField = highlightFields.get("title");
            Text[] fragments = highlightField.getFragments();
            goods.setTitle( fragments[0].string());
            return goods;
        }).collect(Collectors.toList());
        responseVo.setGoodsList(goodsList);


        //解析aggs获取所有的聚合
        Map<String, Aggregation> aggregationMap = response.getAggregations().asMap();
        //获取品牌id的聚合
        ParsedLongTerms brandIdAgg = (ParsedLongTerms) aggregationMap.get("brandIdAgg");
        List<? extends Terms.Bucket> brandBuckets = brandIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(brandBuckets)){

            responseVo.setBrands(brandBuckets.stream().map(bucket->{
                BrandEntity brandEntity = new BrandEntity();
                brandEntity.setId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());
                //获取品牌id的子聚合
                Map<String, Aggregation> subAggregationMap = ((Terms.Bucket) bucket).getAggregations().asMap();
                //解析名称子聚合
                ParsedStringTerms brandNameAgg = (ParsedStringTerms) subAggregationMap.get("brandNameAgg");
                List<? extends Terms.Bucket> nameAggBuckets = brandNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(nameAggBuckets)){
                    brandEntity.setName(nameAggBuckets.get(0).getKeyAsString());
                }
                //解析日志子聚合
                ParsedStringTerms logoAgg = (ParsedStringTerms) subAggregationMap.get("logoAgg");
                List<? extends Terms.Bucket> logoAggBuckets = logoAgg.getBuckets();
                if (!CollectionUtils.isEmpty(logoAggBuckets)){
                    brandEntity.setLogo(logoAggBuckets.get(0).getKeyAsString());
                }
                return brandEntity;
            }).collect(Collectors.toList()));
        }
        //获取并解析categoryIdAgg
        ParsedLongTerms categoryIdAgg = (ParsedLongTerms) aggregationMap.get("categoryIdAgg");
        List<? extends Terms.Bucket> categoryIdAggBuckets = categoryIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(categoryIdAggBuckets)){
            responseVo.setCategories(categoryIdAggBuckets.stream().map(bucket->{
                CategoryEntity categoryEntity = new CategoryEntity();
                categoryEntity.setId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());
                //通过子聚合设置categoryName
                ParsedStringTerms categoryNameAgg = ((Terms.Bucket) bucket).getAggregations().get("categoryNameAgg");
                List<? extends Terms.Bucket> categoryNameAggBuckets = categoryNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(categoryNameAggBuckets)){
                    categoryEntity.setName(categoryNameAggBuckets.get(0).getKeyAsString());
                }
                return categoryEntity;
            }).collect(Collectors.toList()));
        }
        //获取并解析AttrAgg
        ParsedNested attrAgg = (ParsedNested) aggregationMap.get("attrAgg");
        //获取嵌套集合attrIdAgg
        ParsedLongTerms attrIdAgg = (ParsedLongTerms)attrAgg.getAggregations().get("attrIdAgg");
        List<? extends Terms.Bucket> buckets = attrIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(buckets)){
            responseVo.setFilters(buckets.stream().map(bucket->{
                SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();
                //获取桶中的key设置为
                searchResponseAttrVo.setAttrId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());
                //通过获取子聚合设置AttrName
                Map<String, Aggregation> subAggregationMap = ((Terms.Bucket) bucket).getAggregations().asMap();
                ParsedStringTerms attrNameAgg = (ParsedStringTerms) subAggregationMap.get("attrNameAgg");
                List<? extends Terms.Bucket> nameAggBuckets = attrNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(nameAggBuckets)){
                    searchResponseAttrVo.setAttrName(nameAggBuckets.get(0).getKeyAsString());
                }
                //获取attrValueAgg子聚合设置values
                ParsedStringTerms attrValueAgg = (ParsedStringTerms) subAggregationMap.get("attrValueAgg");
                List<? extends Terms.Bucket> buckets1 = attrValueAgg.getBuckets();
                if (!CollectionUtils.isEmpty(buckets1)){
                    searchResponseAttrVo.setAttrValues(buckets1.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList()));
                }
                return  searchResponseAttrVo;
            }).collect(Collectors.toList()));
        }


        return responseVo;
    }


    private SearchSourceBuilder buildDsl(SearchParamVo paramVo){

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        String keyword = paramVo.getKeyword();
        if (StringUtils.isBlank(keyword)){
            return sourceBuilder;
        }
        //构建Dsl语句
        //1.构建查询以及过滤的条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);
        //1.1构建匹配查询
        boolQueryBuilder.must(QueryBuilders.matchQuery("title",keyword).operator(Operator.OR));
        //1.2过滤条件
        //1.2.1品牌过滤
        List<Long> brandId = paramVo.getBrandId();
        if (!CollectionUtils.isEmpty(brandId)){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId",brandId));
        }
        //1.2.2分类过滤
        List<Long> categoryId = paramVo.getCategoryId();
        if (!CollectionUtils.isEmpty(categoryId)){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("categoryId",categoryId));
        }
        //1.2.3价格区间过滤
        Double priceFrom = paramVo.getPriceFrom();
        Double priceTo = paramVo.getPriceTo();
        if (priceFrom!=null ||priceTo!=null){
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("price");
            if (priceFrom!=null ){
                rangeQuery.gte(priceFrom);
            }
            if (priceTo!=null ){
                rangeQuery.lte(priceTo);
            }
            boolQueryBuilder.filter(rangeQuery);
        }
        //1.2.4是否有货过滤
        Boolean store = paramVo.getStore();
        if (store!=null){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("store",store));
        }
        //1.2.5规格参数过滤
        List<String> props = paramVo.getProps();
        if (!CollectionUtils.isEmpty(props)){
            props.forEach(prop->{
                String[] attr = StringUtils.split(prop, ":");
                if (attr!=null && attr.length==2){
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    boolQuery.must(QueryBuilders.termQuery("searchAttrs.attrId",attr[0]));
                    //规格参数值多词条查询条件
                    String[] attrValues = StringUtils.split(attr[1], "-");
                    boolQuery.must(QueryBuilders.termsQuery("searchAttrs.attrValue",attrValues));
                    boolQueryBuilder.filter(QueryBuilders.nestedQuery("searchAttrs",boolQuery, ScoreMode.None));
                }
            });
        }
        //2.排序
        Integer sort = paramVo.getSort();
        if (sort!=null){
            switch (sort){
                case 1: sourceBuilder.sort("price", SortOrder.DESC); break;
                case 2: sourceBuilder.sort("price", SortOrder.ASC); break;
                case 3: sourceBuilder.sort("sales", SortOrder.DESC); break;
                case 4: sourceBuilder.sort("createTime", SortOrder.DESC); break;
                default:
                    break;
            }
        }
        //3.分页
        Integer pageNum = paramVo.getPageNum();
        Integer pageSize = paramVo.getPageSize();
        sourceBuilder.from((pageNum-1)*pageSize);
        sourceBuilder.size(pageSize);
        //4.高亮
        sourceBuilder.highlighter(new HighlightBuilder().field("title").preTags("<em>").postTags("</em>"));
        // 5. 构建聚合
        // 5.1. 构建品牌聚合
        sourceBuilder.aggregation(AggregationBuilders.terms("brandIdAgg").field("brandId")
                .subAggregation(AggregationBuilders.terms("brandNameAgg").field("brandName"))
                .subAggregation(AggregationBuilders.terms("logoAgg").field("logo")));

        // 5.2. 构建分类聚合
        sourceBuilder.aggregation(AggregationBuilders.terms("categoryIdAgg").field("categoryId")
                .subAggregation(AggregationBuilders.terms("categoryNameAgg").field("categoryName")));

        // 5.3. 构建规格参数的嵌套聚合
        sourceBuilder.aggregation(AggregationBuilders.nested("attrAgg", "searchAttrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("searchAttrs.attrId")
                        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("searchAttrs.attrName"))
                        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("searchAttrs.attrValue"))));

        // 6. 构建结果集过滤
        sourceBuilder.fetchSource(new String[]{"skuId", "title", "price", "defaultImage" , "subTitle"}, null);



        System.out.println(sourceBuilder);
        return sourceBuilder;
    }
}
