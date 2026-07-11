package com.yy.agent.contract.mapper;

import com.yy.agent.contract.domain.Contract;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ContractMapper {

    Contract selectById(String id);

    List<Contract> selectAll();

    int upsert(Contract contract);
}
