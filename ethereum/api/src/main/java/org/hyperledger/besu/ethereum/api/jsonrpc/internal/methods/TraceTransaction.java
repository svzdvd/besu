/*
 * Copyright ConsenSys AG.
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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.function.Supplier;

public class TraceTransaction extends AbstractTraceByHash implements JsonRpcMethod {
  public TraceTransaction(
      final Supplier<BlockTracer> blockTracerSupplier,
      final ProtocolSchedule protocolSchedule,
      final BlockchainQueries blockchainQueries) {
    super(blockTracerSupplier, blockchainQueries, protocolSchedule);
  }

  @Override
  public String getName() {
    return RpcMethod.TRACE_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Hash transactionHash = requestContext.getRequiredParameter(0, Hash.class);
    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(),
        arrayNodeFromTraceStream(resultByTransactionHash(transactionHash)));
  }
}
