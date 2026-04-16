package com.yy.agent.contractmvp.mapper;

import com.yy.agent.contractmvp.domain.ClauseChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ClauseChunkMapper {

    ClauseChunk selectOne(@Param("contractId") String contractId, @Param("chunkId") String chunkId);

    List<ClauseChunk> selectByContractId(@Param("contractId") String contractId);

    int deleteByContractId(@Param("contractId") String contractId);

    int insertBatch(@Param("chunks") List<ClauseChunk> chunks);
}
