package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(long id) {
        // 1. 查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在!");
        }
        // 2. 查询blog相关用户
        queryBlogUser(blog);
        // 3. 查询用户是否点赞
        isLiked(blog);
        return Result.ok(blog);
    }

    private void isLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录
            return;
        }
        Long userId = user.getId();

        String blogKey = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(blogKey, userId.toString());
        if (score != null) {
            blog.setIsLike(true);
        } else {
            blog.setIsLike(false);
        }

    }

    @Override
    public Result likeBlog(Long id) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录
            return Result.fail("用户未登录");
        }
        Long userId = user.getId();
        String blogKey = BLOG_LIKED_KEY + id;
        // 2.判断当前登录用户是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(blogKey, userId.toString());
        // 3.如果未点赞可以点赞
        if (score == null) {    // 以防空指针
            // 3.1数据库点赞 +1
            boolean updateSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 3.2将用户信息存入Redis的set集合
            if (updateSuccess) {
                stringRedisTemplate.opsForZSet().add(blogKey, userId.toString(), System.currentTimeMillis());   // 时间戳作为score存入redis中
            }
        } else {
            // 4.如果已点赞则取消点赞
            // 4.1数据库点赞 -1
            boolean updateSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 4.2将用户信息从Redis的set集合中去除
            if (updateSuccess) {
                stringRedisTemplate.opsForZSet().remove(blogKey, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String blogKey = BLOG_LIKED_KEY + id;
        // 1.查询top5的点赞用户
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(blogKey, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2.解析出用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        // 3.根据用户id查找用户
        List<UserDTO> userDTOS = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        // 4.返回
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("保存笔记失败!");
        }
        // 查询笔记作者所有粉丝 select * from tb_follow where follow_user_id = ?
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        for (Follow follow : follows) {
            // 获取粉丝id
            Long followUserId = follow.getUserId();
            // 推送 添加到sortedSet中
            String key = FEED_KEY + followUserId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(long max, Integer offset) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 查询收件箱    ZREVRANGEBYSCORE key Max Min LIMIT offset count
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());
        // 解析数据: blogId score(时间戳) offset
        long minTime = 0;
        int offsetCount = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            // 获取id
            ids.add(Long.valueOf(typedTuple.getValue()));
            // 获取分数(时间戳)
            Long time = typedTuple.getScore().longValue();
            if (time == minTime) {
                offsetCount++;
            } else {
                minTime = time;
                offsetCount = 1;
            }
        }
        // 根据id查询 blog 封装并返回
        List<Blog> blogs = listByIds(ids);
        for (Blog blog : blogs) {
            // 查询blog相关用户
            queryBlogUser(blog);
            // 查询用户是否点赞
            isLiked(blog);
        }
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(offsetCount);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
