package com.ruoyi.system.service.cost;

import com.ruoyi.system.domain.cost.CostAccessProfile;

import java.util.List;

public interface ICostAccessProfileService {
    List<CostAccessProfile> selectAccessProfileList(CostAccessProfile query);

    List<CostAccessProfile> selectAccessProfileOptions(CostAccessProfile query);

    CostAccessProfile selectAccessProfileById(Long profileId);

    boolean checkProfileCodeUnique(CostAccessProfile profile);

    int insertAccessProfile(CostAccessProfile profile);

    int updateAccessProfile(CostAccessProfile profile);

    int deleteAccessProfileByIds(Long[] profileIds);
}
