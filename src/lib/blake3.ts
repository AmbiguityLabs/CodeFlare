// Pure TypeScript BLAKE3 implementation (32-byte digest) matching Wrangler asset hashing

const IV = new Uint32Array([
  0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
  0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19,
]);

const MSG_PERMUTATION = [2, 6, 3, 10, 7, 0, 4, 13, 1, 11, 12, 5, 9, 14, 15, 8];

const CHUNK_START = 1;
const CHUNK_END = 2;
const PARENT = 4;
const ROOT = 8;

const BLOCK_LEN = 64;
const CHUNK_LEN = 1024;

function rotr(x: number, n: number): number {
  return (x >>> n) | (x << (32 - n));
}

function g(
  state: Uint32Array,
  a: number,
  b: number,
  c: number,
  d: number,
  mx: number,
  my: number,
): void {
  state[a] = (state[a] + state[b] + mx) | 0;
  state[d] = rotr(state[d] ^ state[a], 16);
  state[c] = (state[c] + state[d]) | 0;
  state[b] = rotr(state[b] ^ state[c], 12);
  state[a] = (state[a] + state[b] + my) | 0;
  state[d] = rotr(state[d] ^ state[a], 8);
  state[c] = (state[c] + state[d]) | 0;
  state[b] = rotr(state[b] ^ state[c], 7);
}

function round(state: Uint32Array, m: Uint32Array): void {
  g(state, 0, 4, 8, 12, m[0], m[1]);
  g(state, 1, 5, 9, 13, m[2], m[3]);
  g(state, 2, 6, 10, 14, m[4], m[5]);
  g(state, 3, 7, 11, 15, m[6], m[7]);
  g(state, 0, 5, 10, 15, m[8], m[9]);
  g(state, 1, 6, 11, 12, m[10], m[11]);
  g(state, 2, 7, 8, 13, m[12], m[13]);
  g(state, 3, 4, 9, 14, m[14], m[15]);
}

function permute(m: Uint32Array): void {
  const copy = new Uint32Array(m);
  for (let i = 0; i < 16; i++) {
    m[i] = copy[MSG_PERMUTATION[i]];
  }
}

function wordsFromLittleEndianBytes(bytes: Uint8Array, outWords: Uint32Array): void {
  outWords.fill(0);
  const len = Math.min(bytes.length, 64);
  for (let i = 0; i < len; i++) {
    outWords[i >>> 2] |= (bytes[i] & 0xff) << ((i & 3) * 8);
  }
}

function compress(
  cv: Uint32Array,
  blockWords: Uint32Array,
  counter: bigint,
  blockLen: number,
  flags: number,
): Uint32Array {
  const state = new Uint32Array(16);
  state.set(cv.subarray(0, 8), 0);
  state.set(IV.subarray(0, 4), 8);
  state[12] = Number(counter & 0xffffffffn);
  state[13] = Number((counter >> 32n) & 0xffffffffn);
  state[14] = blockLen;
  state[15] = flags;

  const m = new Uint32Array(blockWords);
  for (let r = 0; r < 7; r++) {
    round(state, m);
    if (r < 6) permute(m);
  }

  for (let i = 0; i < 8; i++) {
    state[i] ^= state[i + 8];
    state[i + 8] ^= cv[i];
  }
  return state;
}

interface OutputNode {
  inputCv: Uint32Array;
  blockWords: Uint32Array;
  counter: bigint;
  blockLen: number;
  flags: number;
}

function chainingValue(node: OutputNode): Uint32Array {
  return compress(node.inputCv, node.blockWords, node.counter, node.blockLen, node.flags).subarray(0, 8);
}

function rootBytes(node: OutputNode, outLen: number): Uint8Array {
  const out = new Uint8Array(outLen);
  let outputCounter = 0n;
  let offset = 0;
  while (offset < outLen) {
    const words = compress(
      node.inputCv,
      node.blockWords,
      outputCounter,
      node.blockLen,
      node.flags | ROOT,
    );
    for (let i = 0; i < 16 && offset < outLen; i++) {
      const w = words[i];
      for (let b = 0; b < 4 && offset < outLen; b++) {
        out[offset++] = (w >>> (b * 8)) & 0xff;
      }
    }
    outputCounter += 1n;
  }
  return out;
}

function parentOutput(leftCv: Uint32Array, rightCv: Uint32Array): OutputNode {
  const blockWords = new Uint32Array(16);
  blockWords.set(leftCv.subarray(0, 8), 0);
  blockWords.set(rightCv.subarray(0, 8), 8);
  return {
    inputCv: new Uint32Array(IV),
    blockWords,
    counter: 0n,
    blockLen: BLOCK_LEN,
    flags: PARENT,
  };
}

export function blake3Hash(input: Uint8Array, outLen = 32): Uint8Array {
  const cvStack: Uint32Array[] = [];
  let chunkCounter = 0n;
  let pos = 0;

  if (input.length === 0) {
    const emptyNode: OutputNode = {
      inputCv: new Uint32Array(IV),
      blockWords: new Uint32Array(16),
      counter: 0n,
      blockLen: 0,
      flags: CHUNK_START | CHUNK_END,
    };
    return rootBytes(emptyNode, outLen);
  }

  while (pos < input.length) {
    const chunkEnd = Math.min(pos + CHUNK_LEN, input.length);
    const chunkSlice = input.subarray(pos, chunkEnd);
    let cv = new Uint32Array(IV);
    let chunkPos = 0;
    let blocksCompressed = 0;
    let lastNode: OutputNode | null = null;

    while (chunkPos < chunkSlice.length || blocksCompressed === 0) {
      const end = Math.min(chunkPos + BLOCK_LEN, chunkSlice.length);
      const block = chunkSlice.subarray(chunkPos, end);
      const blockWords = new Uint32Array(16);
      wordsFromLittleEndianBytes(block, blockWords);

      let flags = 0;
      if (blocksCompressed === 0) flags |= CHUNK_START;
      const isLastBlockInChunk = end === chunkSlice.length;
      if (isLastBlockInChunk) flags |= CHUNK_END;

      const node: OutputNode = {
        inputCv: cv,
        blockWords,
        counter: chunkCounter,
        blockLen: block.length,
        flags,
      };

      if (isLastBlockInChunk) {
        lastNode = node;
        break;
      } else {
        cv = chainingValue(node);
      }

      chunkPos = end;
      blocksCompressed++;
    }

    const isLastChunk = chunkEnd === input.length;
    if (isLastChunk && cvStack.length === 0 && lastNode) {
      return rootBytes(lastNode, outLen);
    }

    let nodeCv = chainingValue(lastNode!);
    let totalChunks = chunkCounter + 1n;

    if (!isLastChunk) {
      while ((totalChunks & 1n) === 0n && cvStack.length > 0) {
        const left = cvStack.pop()!;
        nodeCv = chainingValue(parentOutput(left, nodeCv));
        totalChunks >>= 1n;
      }
      cvStack.push(nodeCv);
    } else {
      let currentOutput = lastNode!;
      while (cvStack.length > 0) {
        const left = cvStack.pop()!;
        const rightCv = chainingValue(currentOutput);
        currentOutput = parentOutput(left, rightCv);
      }
      return rootBytes(currentOutput, outLen);
    }

    chunkCounter += 1n;
    pos = chunkEnd;
  }

  return new Uint8Array(outLen);
}

export function blake3Hex(input: Uint8Array): string {
  const bytes = blake3Hash(input, 32);
  let hex = '';
  for (let i = 0; i < bytes.length; i++) {
    hex += bytes[i].toString(16).padStart(2, '0');
  }
  return hex;
}
