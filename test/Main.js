export const delay = ms => () => new Promise(resolve => setTimeout(() => resolve(ms), ms));
export const failAfter = ms => () => new Promise((_, reject) => setTimeout(() => reject(new Error(`timed out after ${ms}ms`)), ms));
