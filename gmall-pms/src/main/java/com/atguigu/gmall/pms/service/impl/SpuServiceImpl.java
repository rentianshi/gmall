package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.feign.GmallPmsClient;
import com.atguigu.gmall.pms.mapper.*;


import com.atguigu.gmall.pms.service.SpuAttrValueService;

import com.atguigu.gmall.pms.service.SpuDescService;
import com.atguigu.gmall.pms.vo.SkuVo;
import com.atguigu.gmall.pms.vo.SpuAttrValueVo;
import com.atguigu.gmall.pms.vo.SpuVo;
import com.atguigu.gmall.sms.vo.SkuSaleVo;
import io.seata.spring.annotation.GlobalTransactional;
import org.apache.commons.lang.StringUtils;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;


import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.service.SpuService;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;


@Service("spuService")
public class SpuServiceImpl extends ServiceImpl<SpuMapper, SpuEntity> implements SpuService {

    @Resource
    private SpuDescMapper descMapper;
    @Resource
    private SpuAttrValueService spuAttrValueService;

    @Resource
    private SkuImagesMapper skuImagesMapper;

    @Resource
    private SkuMapper skuMapper;

    @Resource
    private SkuAttrValueMapper skuAttrValueMapper;

    @Resource
    private GmallPmsClient gmallPmsClient;

    @Resource
    private SpuDescService spuDescService;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SpuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SpuEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public PageResultVo querySpuByCidAndPage(Long cid, PageParamVo paramVo) {
        QueryWrapper<SpuEntity> wrapper = new QueryWrapper<>();
        //是，0-查询全部分类；其他-查询指定分类stat
        if (cid!=0){
            wrapper.eq("category_id",cid);
        }

        String key = paramVo.getKey();
        //判断关键字是否为空
        if (StringUtils.isNotBlank(key)){
            wrapper.and(t -> t.like("id",key).or().like("name",key));
        }

        IPage<SpuEntity> page = this.page(
                paramVo.getPage(),
               wrapper
        );

        return new PageResultVo(page);
    }

    @GlobalTransactional
    @Override
    public void bigSave(SpuVo spu) {
        //保存spu的相关信息
            //1.保存spu的基本信息  pms_spu
        saveSpu(spu);
        //2.保存spu的描述信息  pms_spu_desc
        //saveSpuDesc(spu);
        spuDescService.saveSpuDesc(spu);

        //3.保存spu的基本属性信息 pms_spu_attr
        saveBaseAttr(spu);
        //保存sku的相关信息

        saveSku(spu);

    }

    private void saveSku(SpuVo spu) {
        List<SkuVo> skus = spu.getSkus();
        if (!CollectionUtils.isEmpty(skus)){
            SkuEntity skuEntity = new SkuEntity();
            skus.forEach(sku ->{
                //1.保存sku的基本信息  pms_sku
                sku.setSpuId(spu.getId());
                sku.setBrandId(spu.getBrandId());
                sku.setCatagoryId(spu.getCategoryId());
                //设置默认图片
                List<String> images = sku.getImages();
                if (!CollectionUtils.isEmpty(images)){
                    sku.setDefaultImage(StringUtils.isNotBlank(sku.getDefaultImage())?sku.getDefaultImage():images.get(0));
                }
                skuMapper.insert(sku);
                //保存到数据库后获取id回显
                Long skuId = sku.getId();
                System.out.println(sku.getId());

                //2.保存sku的图片信息  pms_sku_images
                List<String> images1 = sku.getImages();
                if (!CollectionUtils.isEmpty(images)){
                    images1.forEach(image->{
                        SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                        skuImagesEntity.setSkuId(sku.getId());
                        skuImagesEntity.setUrl(image);
                        skuImagesEntity.setDefaultStatus(StringUtils.equals(sku.getDefaultImage(),image)?1:0);
                        skuImagesMapper.insert(skuImagesEntity);
                    });
                }
                //3.保存sku的销售属性  pms_sku_attr_value
                List<SkuAttrValueEntity> saleAttrs = sku.getSaleAttrs();
                if (!CollectionUtils.isEmpty(saleAttrs)){
                    saleAttrs.forEach(attr->{
                        SkuAttrValueEntity skuAttrValueEntity = new SkuAttrValueEntity();
                        BeanUtils.copyProperties(attr,skuAttrValueEntity);
                        skuAttrValueEntity.setSkuId(sku.getId());
                        skuAttrValueMapper.insert(skuAttrValueEntity);
                    });
                }
                //保存sku的营销信息
                SkuSaleVo skuSaleVo = new SkuSaleVo();
                BeanUtils.copyProperties(sku,skuSaleVo);
                skuSaleVo.setSkuId(sku.getId());
                gmallPmsClient.saveSales(skuSaleVo);
            } );
        }
    }

    private void saveBaseAttr(SpuVo spu) {
        List<SpuAttrValueVo> baseAttrs = spu.getBaseAttrs();
        if (!CollectionUtils.isEmpty(baseAttrs)){

            List<SpuAttrValueEntity> list = baseAttrs.stream().map(attr -> {
                SpuAttrValueEntity spuAttrValueEntity = new SpuAttrValueEntity();
                BeanUtils.copyProperties(attr,spuAttrValueEntity);
                spuAttrValueEntity.setSpuId(spu.getId());
                return spuAttrValueEntity;
            }).collect(Collectors.toList());
            spuAttrValueService.saveBatch(list);
        }
    }



    private void saveSpu(SpuVo spu) {
        spu.setCreateTime(new Date());
        spu.setUpdateTime(spu.getCreateTime());
        this.save(spu);
    }

}