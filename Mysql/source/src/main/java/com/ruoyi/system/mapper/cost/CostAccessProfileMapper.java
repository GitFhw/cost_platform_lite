package com.ruoyi.system.mapper.cost;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.system.domain.cost.CostAccessProfile;

import java.util.List;

public interface CostAccessProfileMapper extends BaseMapper<CostAccessProfile> {
    List<CostAccessProfile> selectAccessProfileList(CostAccessProfile query);

    CostAccessProfile selectAccessProfileById(Long profileId);
}
