package com.cug.geotext.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cug.geotext.common.BusinessException;
import com.cug.geotext.dto.UserRequest;
import com.cug.geotext.entity.AppUser;
import com.cug.geotext.mapper.AppUserMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserAdminService {
    private final AppUserMapper mapper;private final PasswordEncoder encoder;public UserAdminService(AppUserMapper mapper,PasswordEncoder encoder){this.mapper=mapper;this.encoder=encoder;}
    public List<AppUser>list(){return mapper.selectList(new LambdaQueryWrapper<AppUser>().orderByAsc(AppUser::getId));}
    public AppUser create(UserRequest request){if(mapper.selectCount(new LambdaQueryWrapper<AppUser>().eq(AppUser::getUsername,request.username()))>0)throw new BusinessException(409,"用户名已存在");if(request.password()==null||request.password().length()<8)throw new BusinessException(400,"密码至少 8 位");AppUser user=new AppUser();apply(user,request,true);mapper.insert(user);return user;}
    public AppUser update(long id,UserRequest request){AppUser user=get(id);if(!user.getUsername().equals(request.username())&&mapper.selectCount(new LambdaQueryWrapper<AppUser>().eq(AppUser::getUsername,request.username()))>0)throw new BusinessException(409,"用户名已存在");apply(user,request,false);mapper.updateById(user);return user;}
    public void delete(long id){AppUser user=get(id);if("admin".equals(user.getUsername()))throw new BusinessException(409,"不能删除内置管理员");mapper.deleteById(id);}
    private AppUser get(long id){AppUser user=mapper.selectById(id);if(user==null)throw new BusinessException(404,"用户不存在");return user;}
    private void apply(AppUser user,UserRequest request,boolean create){user.setUsername(request.username());user.setDisplayName(request.displayName());user.setRole(request.role()==null?"USER":request.role());user.setEnabled(request.enabled()==null||request.enabled());if(request.password()!=null&&!request.password().isBlank())user.setPasswordHash(encoder.encode(request.password()));OffsetDateTime now=OffsetDateTime.now();user.setUpdateTime(now);if(create)user.setCreateTime(now);}
}
