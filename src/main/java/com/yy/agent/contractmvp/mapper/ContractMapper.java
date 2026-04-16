package com.yy.agent.contractmvp.mapper;

import com.yy.agent.contractmvp.domain.Contract;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContractMapper {

    Contract selectById(String id);

    int upsert(Contract contract);
}
