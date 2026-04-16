package com.yy.agent.contractmvp.mapper;

import com.yy.agent.contractmvp.mapper.model.ApprovalRecordRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ApprovalRecordMapper {

    List<ApprovalRecordRow> selectByContractId(@Param("contractId") String contractId);

    int deleteByContractId(@Param("contractId") String contractId);

    int insertBatch(@Param("rows") List<ApprovalRecordRow> rows);
}
