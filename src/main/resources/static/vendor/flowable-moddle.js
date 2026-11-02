/*
 * 最小化 Flowable moddle 扩展描述符
 * 作用：让 bpmn-js 认识 http://flowable.org/bpmn 命名空间，
 *      从而正确解析并回写 flowable:assignee 等属性。
 * 说明：npm 上的 camunda-bpmn-moddle 并没有发布 UMD 构建产物，
 *      原来通过 <script src=".../dist/camunda-bpmn-moddle.umd.js"> 引入必然 404。
 */
window.FlowableModdleDescriptor = {
    name: 'Flowable',
    uri: 'http://flowable.org/bpmn',
    prefix: 'flowable',
    xml: { tagAlias: 'lowerCase' },
    associations: [],
    types: [
        {
            name: 'UserTask',
            extends: ['bpmn:UserTask'],
            properties: [
                { name: 'assignee', isAttr: true, type: 'String' },
                { name: 'candidateUsers', isAttr: true, type: 'String' },
                { name: 'candidateGroups', isAttr: true, type: 'String' },
                { name: 'dueDate', isAttr: true, type: 'String' },
                { name: 'priority', isAttr: true, type: 'String' },
                { name: 'formKey', isAttr: true, type: 'String' },
                { name: 'category', isAttr: true, type: 'String' }
            ]
        },
        {
            name: 'Process',
            extends: ['bpmn:Process'],
            properties: [
                { name: 'candidateStarterUsers', isAttr: true, type: 'String' },
                { name: 'candidateStarterGroups', isAttr: true, type: 'String' },
                { name: 'versionTag', isAttr: true, type: 'String' }
            ]
        },
        {
            name: 'ServiceTask',
            extends: ['bpmn:ServiceTask'],
            properties: [
                { name: 'class', isAttr: true, type: 'String' },
                { name: 'expression', isAttr: true, type: 'String' },
                { name: 'delegateExpression', isAttr: true, type: 'String' },
                { name: 'resultVariable', isAttr: true, type: 'String' }
            ]
        },
        {
            name: 'ScriptTask',
            extends: ['bpmn:ScriptTask'],
            properties: [
                { name: 'resultVariable', isAttr: true, type: 'String' }
            ]
        },
        {
            name: 'SequenceFlow',
            extends: ['bpmn:SequenceFlow'],
            properties: [
                { name: 'skipExpression', isAttr: true, type: 'String' }
            ]
        },
        {
            name: 'ExecutionListener',
            superClass: ['Element'],
            properties: [
                { name: 'event', isAttr: true, type: 'String' },
                { name: 'class', isAttr: true, type: 'String' },
                { name: 'expression', isAttr: true, type: 'String' },
                { name: 'delegateExpression', isAttr: true, type: 'String' }
            ]
        },
        {
            name: 'TaskListener',
            superClass: ['Element'],
            properties: [
                { name: 'event', isAttr: true, type: 'String' },
                { name: 'class', isAttr: true, type: 'String' },
                { name: 'expression', isAttr: true, type: 'String' },
                { name: 'delegateExpression', isAttr: true, type: 'String' }
            ]
        }
    ]
};
