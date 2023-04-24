package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result quereyList() {

        // 1.从redis中查询商铺类别缓存
        String shopTypeJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);
        // 2.判断缓存是否命中
        if (StrUtil.isNotBlank(shopTypeJson)) {
            // 3.命中，返回
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(shopTypes);
        }

        // 4.未命中,根据id查询数据库
        List<ShopType> shopTypes = query().orderByAsc("sort").list();

        // 5.判断商铺类别是否存在
        if (shopTypes == null) {
            // 6.不存在，报错404
            Result.fail("商铺类别查询错误");
        }
        // 7.存在，将商户信息写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY,JSONUtil.toJsonStr(shopTypes),CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);
        // 8.返回商铺信息
        return Result.ok(shopTypes);
    }
}
