FROM node:22-alpine
WORKDIR /app
COPY package.json ./
COPY src ./src
COPY public ./public
USER node
EXPOSE 3001
CMD ["node", "src/main.js"]
