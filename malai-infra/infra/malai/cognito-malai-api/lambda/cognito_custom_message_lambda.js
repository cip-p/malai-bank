const fs = require('fs');
const path = require('path');

const template = (name) => fs.readFileSync(path.join(__dirname, 'templates', name), 'utf8');

const CODE_EMAIL = template('code.html');
const FORGOT_PASSWORD_EMAIL = template('reset-password-code.html');

const CODE_TRIGGER_SOURCES = [
    "CustomMessage_SignUp",
    "CustomMessage_ResendCode",
    "CustomMessage_Authentication"
];

module.exports.handler = async (event) => {
    console.log(JSON.stringify({ triggerSource: event.triggerSource }));

    if (CODE_TRIGGER_SOURCES.includes(event.triggerSource)) {
        event.response.emailSubject = "Your Malai Bank code";
        event.response.emailMessage = CODE_EMAIL.replace("{verification_code}", event.request.codeParameter);
    } else if (event.triggerSource === "CustomMessage_ForgotPassword") {
        event.response.emailSubject = "Your password reset code";
        event.response.emailMessage = FORGOT_PASSWORD_EMAIL.replace("{verification_code}", event.request.codeParameter);
    }

    return event;
};
