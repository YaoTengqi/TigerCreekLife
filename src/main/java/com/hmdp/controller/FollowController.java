package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;

    @PutMapping("/{id}/{isFollowed}")
    public Result follow(@PathVariable("id") long id, @PathVariable("isFollowed") boolean isFollowed) {
        return followService.follow(id, isFollowed);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") long id) {
        return followService.isFollow(id);
    }
}
