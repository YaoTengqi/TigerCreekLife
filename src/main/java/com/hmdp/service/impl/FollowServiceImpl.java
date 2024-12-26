package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    /**
     * 进行关注 or 取关
     *
     * @param followedId
     * @param isFollowed
     * @return
     */
    @Override
    public Result follow(long followedId, boolean isFollowed) {
        // 获取当前登录的用户信息
        Long userId = UserHolder.getUser().getId();
        // 判断是关注还是取关
        if (isFollowed) {
            // 关注
            Follow newFollow = new Follow();
            newFollow.setFollowUserId(followedId);  // 被关注者id
            newFollow.setUserId(userId);    // 粉丝id
            save(newFollow);// 存入数据库中
        } else {
            // 取关 delete from tb_follow where user_id = ? and follow_user_id = ?
            remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followedId));
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query()
                .eq("user_id", userId)
                .eq("follow_user_id", followUserId)
                .count();
        return Result.ok(count > 0);
    }
}
