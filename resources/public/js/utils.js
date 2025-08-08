// Secure alphanumeric generator for browser
class BrowserAccountGenerator {
  constructor() {
    // Different character sets for different security/usability tradeoffs
    this.charsets = {
      // 62 chars: Maximum entropy but includes confusable characters
      alphanumeric: '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ',

      // 57 chars: Removes confusable characters (0/O, 1/l/I, 5/S)
      safe: '23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ',

      // 32 chars: Base32 - good balance of security and readability
      base32: '23456789ABCDEFGHJKLMNPQRSTUVWXYZ',

      // 36 chars: Case-insensitive alphanumeric
      lowercase: '0123456789abcdefghijklmnopqrstuvwxyz'
    };
  }

  generate(length = 16, charset = 'safe') {
    const chars = this.charsets[charset];
    if (!chars) throw new Error(`Unknown charset: ${charset}`);

    // Calculate how many random bytes we need
    // We need extra for rejection sampling
    const bytesNeeded = Math.ceil(length * 1.5);
    const randomBytes = new Uint8Array(bytesNeeded);

    crypto.getRandomValues(randomBytes);

    let result = '';
    let byteIndex = 0;

    while (result.length < length) {
      if (byteIndex >= randomBytes.length) {
        // Need more random bytes
        crypto.getRandomValues(randomBytes);
        byteIndex = 0;
      }

      const byte = randomBytes[byteIndex++];

      // Rejection sampling for uniform distribution
      // This is crucial for security!
      if (byte < 256 - (256 % chars.length)) {
        result += chars[byte % chars.length];
      }
    }

    return result;
  }

  // Calculate bits of entropy
  calculateEntropy(length, charset = 'safe') {
    const chars = this.charsets[charset];
    return length * Math.log2(chars.length);
  }

  // Format for better readability
  format(account, groupSize = 4, separator = '-') {
    return account.match(new RegExp(`.{1,${groupSize}}`, 'g')).join(separator);
  }
}



function generateAccountId() {
  const generator = new BrowserAccountGenerator();
  return generator.format(generator.generate(16, 'base32'));
}

function showToast(message, type = 'success') {
  window.dispatchEvent(new CustomEvent('show-toast', {
    detail: { message, type }
  }));
}
