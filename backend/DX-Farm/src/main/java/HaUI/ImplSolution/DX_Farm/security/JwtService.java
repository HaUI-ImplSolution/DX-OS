package HaUI.ImplSolution.DX_Farm.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

// Ký/xác thực JWT (HS256) - stateless, không lưu session ở backend
@Service
public class JwtService {

  private final Key signingKey;
  private final long expirationMillis;

  public JwtService(@Value("${jwt.secret}") String secret,
      @Value("${jwt.expiration}") long expirationMillis) {
    this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    this.expirationMillis = expirationMillis;
  }

  public String generateToken(UserDetails userDetails) {
    String role = userDetails.getAuthorities().stream().findFirst()
        .map(a -> a.getAuthority().replaceFirst("^ROLE_", "")).orElse("");
    Date now = new Date();
    return Jwts.builder().setSubject(userDetails.getUsername()).claim("role", role).setIssuedAt(now)
        .setExpiration(new Date(now.getTime() + expirationMillis))
        .signWith(signingKey, SignatureAlgorithm.HS256).compact();
  }

  public String extractUsername(String token) {
    return parseClaims(token).getSubject();
  }

  public boolean isTokenValid(String token, UserDetails userDetails) {
    Claims claims = parseClaims(token);
    return claims.getSubject().equals(userDetails.getUsername())
        && claims.getExpiration().after(new Date());
  }

  private Claims parseClaims(String token) {
    return Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token).getBody();
  }
}
