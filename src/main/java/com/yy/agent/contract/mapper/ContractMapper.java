package com.yy.agent.contract.mapper;

import com.yy.agent.contract.domain.Contract;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContractMapper {

    Contract selectById(String id);

    int upsert(Contract contract);
}
