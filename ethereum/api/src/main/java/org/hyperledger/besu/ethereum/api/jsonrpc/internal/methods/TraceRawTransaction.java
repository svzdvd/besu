/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INTERNAL_ERROR;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceTypeParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.util.DomainObjectDecodeUtils;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TraceRawTransaction extends AbstractTraceByBlock implements JsonRpcMethod {
  private static final Logger LOG = LoggerFactory.getLogger(TraceRawTransaction.class);

  public TraceRawTransaction(
      final ProtocolSchedule protocolSchedule,
      final BlockchainQueries blockchainQueries,
      final TransactionSimulator transactionSimulator) {
    super(blockchainQueries, protocolSchedule, transactionSimulator);
  }

  @Override
  public String getName() {
    return transactionSimulator != null ? RpcMethod.TRACE_RAW_TRANSACTION.getMethodName() : null;
  }

  @Override
  protected Object resultByBlockNumber(
      final JsonRpcRequestContext request, final long blockNumber) {
    // this method does not get called because response() does the work
    return null;
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    if (requestContext.getRequest().getParamLength() != 2) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }

    final String rawTransaction = requestContext.getRequiredParameter(0, String.class);

    final Transaction transaction;
    try {
      transaction = DomainObjectDecodeUtils.decodeRawTransaction(rawTransaction);
      LOG.trace("Received raw transaction {}", transaction);
    } catch (final RLPException | IllegalArgumentException e) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }

    final TraceTypeParameter traceTypeParameter =
        requestContext.getRequiredParameter(1, TraceTypeParameter.class);

    final Set<TraceTypeParameter.TraceType> traceTypes = traceTypeParameter.getTraceTypes();
    final DebugOperationTracer tracer = new DebugOperationTracer(buildTraceOptions(traceTypes));
    final long headBlockNumber = blockchainQueries.get().headBlockNumber();
    final Optional<TransactionSimulatorResult> maybeSimulatorResult =
        transactionSimulator.process(
            CallParameter.fromTransaction(transaction),
            buildTransactionValidationParams(),
            tracer,
            headBlockNumber);

    if (maybeSimulatorResult.isEmpty()) {
      return new JsonRpcErrorResponse(requestContext.getRequest().getId(), INTERNAL_ERROR);
    }

    final TransactionTrace transactionTrace =
        new TransactionTrace(
            maybeSimulatorResult.get().getTransaction(),
            maybeSimulatorResult.get().getResult(),
            tracer.getTraceFrames());
    final Optional<Block> maybeBlock =
        blockchainQueries.get().getBlockchain().getBlockByNumber(headBlockNumber);

    if (maybeBlock.isEmpty()) {
      return new JsonRpcErrorResponse(requestContext.getRequest().getId(), INTERNAL_ERROR);
    }

    final Block block = maybeBlock.get();
    final Object response =
        getTraceCallResult(
            protocolSchedule, traceTypes, maybeSimulatorResult, transactionTrace, block);

    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), response);
  }
}
