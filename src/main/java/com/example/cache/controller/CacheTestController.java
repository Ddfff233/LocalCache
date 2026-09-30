package com.example.cache.controller;

import com.example.cache.domain.R;
import com.example.cache.domain.dto.CacheSetDTO;
import com.example.cache.domain.vo.CacheTestVO;
import com.example.cache.service.CacheTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供方法缓存功能的接口测试入口。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/cache/test")
public class CacheTestController {

    private final CacheTestService cacheTestService;

    /**
     * 获取指定标识对应的缓存测试结果。
     *
     * @param id 测试标识
     * @return 统一响应结果
     */
    @GetMapping("/{id}")
    public R<CacheTestVO> getCachedResult(@PathVariable Long id) {
        log.info("接收缓存测试请求，标识：{}", id);
        return R.success(cacheTestService.getCachedResult(id));
    }

    /**
     * 永久写入字符串缓存。
     *
     * @param request 写入请求
     * @return 写入结果
     */
    @PostMapping("/set")
    public R<Boolean> setCache(@RequestBody CacheSetDTO request) {
        log.info("接收缓存写入测试请求");
        cacheTestService.setCache(request.getKey(), request.getValue());
        return R.success(true);
    }

    /**
     * 读取字符串缓存。
     *
     * @param key 缓存键
     * @return 缓存值
     */
    @GetMapping("/get")
    public R<String> getCache(@RequestParam String key) {
        log.info("接收缓存读取测试请求");
        return R.success(cacheTestService.getCache(key));
    }

}
