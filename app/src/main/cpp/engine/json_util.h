#pragma once

#include <string>

/** Escapes a UTF-8 string for inclusion inside a JSON string literal. */
inline std::string sotcJsonEscape(const std::string& input) {
    std::string output;
    output.reserve(input.size() + 16);
    for (char c : input) {
        switch (c) {
            case '"': output += "\\\""; break;
            case '\\': output += "\\\\"; break;
            case '\n': output += "\\n"; break;
            case '\r': output += "\\r"; break;
            case '\t': output += "\\t"; break;
            default:
                if (static_cast<unsigned char>(c) < 0x20) {
                    static const char* hex = "0123456789abcdef";
                    output += "\\u00";
                    output += hex[(static_cast<unsigned char>(c) >> 4) & 0x0F];
                    output += hex[static_cast<unsigned char>(c) & 0x0F];
                } else {
                    output += c;
                }
        }
    }
    return output;
}
