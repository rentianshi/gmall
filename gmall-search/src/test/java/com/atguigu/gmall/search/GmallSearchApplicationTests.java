package com.atguigu.gmall.search;

import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.search.feign.GmallPmsClient;
import com.atguigu.gmall.search.feign.GmallWmsClient;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchAttrValueVo;
import com.atguigu.gmall.search.repository.GoodsRepository;

import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@SpringBootTest
class GmallSearchApplicationTests {

    @Autowired
    ElasticsearchRestTemplate restTemplate;

    @Autowired
    GoodsRepository goodsRepository;

    @Autowired
    GmallPmsClient gmallPmsClient;

    @Autowired
    GmallWmsClient gmallWmsClient;

    @Test
    void contextLoads() {
        restTemplate.createIndex(Goods.class);
        restTemplate.putMapping(Goods.class);

        Integer pageNum = 1;
        Integer pageSize = 100;
        do {
            //分批查询spu
            PageParamVo pageParamVo = new PageParamVo();
            pageParamVo.setPageNum(pageNum);
            pageParamVo.setPageSize(pageSize);
            ResponseVo<List<SpuEntity>> responseVo = this.gmallPmsClient.querySpuByPageJson(pageParamVo);
            List<SpuEntity> spuEntities = responseVo.getData();
            if (CollectionUtils.isEmpty(spuEntities)){
                return;
            }
            //变量当前spu查询sku
            spuEntities.forEach(spuEntity -> {
                ResponseVo<List<SkuEntity>> skuResponseVo = this.gmallPmsClient.querySkuEntitiesBySpuId(spuEntity.getId());
                List<SkuEntity> skuEntities = skuResponseVo.getData();
                if (!CollectionUtils.isEmpty(skuEntities)){
                    //把sku集合转化为goods集合
                    List<Goods> goodsList = skuEntities.stream().map(skuEntity -> {
                        Goods goods = new Goods();
                        //设置sku信息
                        goods.setSkuId(skuEntity.getId());
                        goods.setDefaultImage(skuEntity.getDefaultImage());
                        goods.setPrice(skuEntity.getPrice().doubleValue());
                        goods.setTitle(skuEntity.getTitle());
                        goods.setSubTitle(skuEntity.getSubtitle());

                        goods.setCreateTime(spuEntity.getCreateTime());
                        //设置库存的相关信息
                        ResponseVo<List<WareSkuEntity>> wareResponseVo = this.gmallWmsClient.queryWareSkuBySkuId(skuEntity.getId());
                        List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
                        if(!CollectionUtils.isEmpty(wareSkuEntities)){
                           goods.setSales(wareSkuEntities.stream().map(WareSkuEntity::getSales).reduce((a, b) -> a + b).get());
                           goods.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity ->
                               wareSkuEntity.getStock()-wareSkuEntity.getStockLocked()>0
                           ));
                        }

                        //品牌
                        ResponseVo<BrandEntity> brandEntityResponseVo = this.gmallPmsClient.queryBrandById(skuEntity.getBrandId());
                        BrandEntity brandEntity = brandEntityResponseVo.getData();
                        if(brandEntity != null){
                            goods.setBrandId(brandEntity.getId());
                            goods.setBrandName(brandEntity.getName());
                            goods.setLogo(brandEntity.getLogo());
                        }
                        //分类
                        ResponseVo<CategoryEntity> categoryEntityResponseVo = this.gmallPmsClient.queryCategoryById(skuEntity.getCatagoryId());
                        CategoryEntity categoryEntity = categoryEntityResponseVo.getData();
                        if (categoryEntity != null){
                            goods.setCategoryId(categoryEntity.getId());
                            goods.setCategoryName(categoryEntity.getName());
                        }

                        //声明要获取的searchAttrValueVo的集合
                        List<SearchAttrValueVo> searchAttrValueVos = new ArrayList<>();
                        //查询销售类型的检索参数
                        ResponseVo<List<SkuAttrValueEntity>> skuAttrsResponseVo = this.gmallPmsClient.querySearchSkuAttrValuesByCidAndSkuId(skuEntity.getCatagoryId(), skuEntity.getId());
                        List<SkuAttrValueEntity> skuAttrValueEntities = skuAttrsResponseVo.getData();
                        if (!CollectionUtils.isEmpty(skuAttrValueEntities)){
                            searchAttrValueVos.addAll(skuAttrValueEntities.stream().map(skuAttrValueEntity -> {
                                SearchAttrValueVo searchAttrValueVo = new SearchAttrValueVo();
                                BeanUtils.copyProperties(skuAttrValueEntity,searchAttrValueVo);
                                return searchAttrValueVo;
                            }).collect(Collectors.toList()));
                        }

                        //查询普通类型的检索参数
                        ResponseVo<List<SpuAttrValueEntity>> spuAttrsResponseVo = this.gmallPmsClient.querySearchSpuAttrValuesByCidAndSpuId(spuEntity.getCategoryId(), spuEntity.getId());
                        List<SpuAttrValueEntity> spuAttrValueEntities= spuAttrsResponseVo.getData();
                        if (!CollectionUtils.isEmpty(searchAttrValueVos)){
                            searchAttrValueVos.addAll(spuAttrValueEntities.stream().map(spuAttrValueEntity -> {
                                SearchAttrValueVo searchAttrValueVo = new SearchAttrValueVo();
                                BeanUtils.copyProperties(spuAttrValueEntity,searchAttrValueVo);
                                return searchAttrValueVo;
                            }).collect(Collectors.toList()));
                        }
                        goods.setSearchAttrs(searchAttrValueVos);

                        return goods;
                    }).collect(Collectors.toList());
                    this.goodsRepository.saveAll(goodsList);
                }
            });
            pageNum++;
            pageSize = spuEntities.size();
        }while (pageSize == 100);

    }

}
